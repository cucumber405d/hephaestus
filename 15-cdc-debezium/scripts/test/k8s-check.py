#!/usr/bin/env python3
"""Checks rendered manifests of subproject 15 without a cluster.

usage: k8s-check.py <strimzi-crds.yaml> <rendered.yaml>

1. Every Strimzi resource validates against the served v1 schema of its CRD.
2. KafkaConnect.spec.config carries no key that Strimzi v1 moved into spec fields.
3. The KafkaConnector syncs in a later Argo wave than the ordering Deployment (its Flyway migration creates the
   publication the connector needs; a connector started earlier creates no slot and loses those orders).
"""
import sys

import jsonschema
import yaml

FORBIDDEN_CONNECT_CONFIG = {"group.id", "config.storage.topic", "offset.storage.topic", "status.storage.topic"}


def wave(doc):
    return int(doc["metadata"].get("annotations", {}).get("argocd.argoproj.io/sync-wave", "0"))


def main(crds_path, rendered_path):
    schemas = {}
    for crd in yaml.safe_load_all(open(crds_path)):
        if not crd or crd.get("kind") != "CustomResourceDefinition":
            continue
        group = crd["spec"]["group"]
        for version in crd["spec"]["versions"]:
            if version.get("served"):
                key = (f"{group}/{version['name']}", crd["spec"]["names"]["kind"])
                schemas[key] = version["schema"]["openAPIV3Schema"]

    docs = [d for d in yaml.safe_load_all(open(rendered_path)) if d]
    errors = []
    for doc in docs:
        key = (doc["apiVersion"], doc["kind"])
        if not doc["apiVersion"].startswith("kafka.strimzi.io/"):
            continue
        name = f"{doc['kind']}/{doc['metadata']['name']}"
        if key not in schemas:
            errors.append(f"{name}: no served CRD version {doc['apiVersion']}")
            continue
        for error in jsonschema.Draft7Validator(schemas[key]).iter_errors(doc):
            errors.append(f"{name}: {'/'.join(map(str, error.path))}: {error.message}")
        if doc["kind"] == "KafkaConnect":
            for k in sorted(FORBIDDEN_CONNECT_CONFIG & set(doc["spec"].get("config", {}))):
                errors.append(f"{name}: spec.config.{k} is a spec field in Strimzi v1")

    connectors = [d for d in docs if d["kind"] == "KafkaConnector"]
    ordering = [d for d in docs if d["kind"] == "Deployment" and d["metadata"]["name"] == "ordering"]
    for connector in connectors:
        for deployment in ordering:
            if wave(connector) <= wave(deployment):
                errors.append(
                    f"KafkaConnector/{connector['metadata']['name']}: sync-wave {wave(connector)} "
                    f"must be after Deployment/ordering ({wave(deployment)})"
                )

    for e in errors:
        print("FAIL", e)
    print(f"checked {len(docs)} resources, {len(errors)} problem(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:]))
