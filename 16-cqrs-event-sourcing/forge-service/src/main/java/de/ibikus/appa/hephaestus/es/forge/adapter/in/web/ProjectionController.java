package de.ibikus.appa.hephaestus.es.forge.adapter.in.web;

import de.ibikus.appa.hephaestus.es.forge.application.projection.ProjectionAdmin;
import de.ibikus.appa.hephaestus.es.forge.application.projection.ProjectionStatus;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Replay for administrators: rebuild a read model from the journal; status of every projection. */
@RestController
class ProjectionController {

    private final ProjectionAdmin admin;

    ProjectionController(ProjectionAdmin admin) {
        this.admin = admin;
    }

    @GetMapping("/admin/projections")
    List<ProjectionStatus> status() {
        return admin.status();
    }

    /** Accepted: the read model is empty now and fills again on the runner's next polls. */
    @PostMapping("/admin/projections/{name}/rebuild")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void rebuild(@PathVariable String name) {
        admin.rebuild(name);
    }
}
