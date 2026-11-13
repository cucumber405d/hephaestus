package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.web;

/** Money on the wire: the amount is a string ("12.50") so no client ever parses it as a binary float. */
record PriceJson(String amount, String currency) {}
