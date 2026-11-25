package de.ibikus.appa.hephaestus.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Timestamp;
import de.ibikus.appa.hephaestus.contracts.api.forge.v1.QuoteReply;
import de.ibikus.appa.hephaestus.contracts.events.WorkOrderRequest;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.Test;

/** The generated classes work, and the current request schema reads what a 1.0.0 producer wrote (BACKWARD). */
class ContractsRoundTripTest {

    private static final String ORDER = "6f1c2a8e-0000-4000-8000-000000000001";

    @Test
    void v1RequestReadWithCurrentSchemaHasNoQuote() throws Exception {
        var v1 = new Schema.Parser()
                .parse(Path.of("released/1.0.0/avro/work_order_request.avsc").toFile());
        GenericRecord old = new GenericData.Record(v1);
        old.put("orderId", ORDER);
        old.put("material", "IRON-01");
        old.put("quantity", 5);
        var out = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(out, null);
        new GenericDatumWriter<GenericRecord>(v1).write(old, encoder);
        encoder.flush();

        var reader = new SpecificDatumReader<WorkOrderRequest>(v1, WorkOrderRequest.getClassSchema());
        var read = reader.read(null, DecoderFactory.get().binaryDecoder(out.toByteArray(), null));

        assertThat(read.getOrderId()).isEqualTo(ORDER);
        assertThat(read.getMaterial()).isEqualTo("IRON-01");
        assertThat(read.getQuantity()).isEqualTo(5);
        assertThat(read.getQuoteId()).isNull();
        assertThat(read.getPriceCents()).isNull();
    }

    @Test
    void quoteReplyRoundTrip() throws Exception {
        var reply = QuoteReply.newBuilder()
                .setQuoteId("q-1")
                .setPriceCents(6000)
                .setLeadTimeDays(3)
                .setExpiresAt(Timestamp.newBuilder().setSeconds(1_800_000_000L))
                .build();

        var read = QuoteReply.parseFrom(reply.toByteArray());

        assertThat(read.getPriceCents()).isEqualTo(6000);
        assertThat(read.getLeadTimeDays()).isEqualTo(3);
        assertThat(read).isEqualTo(reply);
    }
}
