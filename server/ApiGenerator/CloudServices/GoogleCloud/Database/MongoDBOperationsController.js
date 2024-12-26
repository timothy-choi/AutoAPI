const AWS = require('aws-sdk');
const axios = require('axios');

exports.QueryOne = async (req, res) => {
    try {
        if (!("filter" in req.body) || req.body.filter == null || req.body.projection == null) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.post(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.QueryMany = async (req, res) => {
    try {
        if (!("filter" in req.body) || req.body.filter == null || req.body.options == null) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.post(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.Aggregate = async (req, res) => {
    try {
        if (!("pipeline" in req.body) || req.body.pipeline == null || req.body.pipeline == {}) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.post(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.CountDocuments = async (req, res) => {
    try {
        if (!("query" in req.body) || req.body.query == null || req.body.query == "") {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.post(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.InsertOne = async (req, res) => {
    try {
        if (!("document" in req.body) || (req.body.document == null)) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.post(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.InsertMany = async (req, res) => {
    try {
        if (!("documents" in req.body) || req.body.documents == null || req.body.documents.length == 0) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.post(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.upsert = async (req, res) => {
    try {
        if (!("query" in req.body) || req.body.query == null || !("updateDoc" in req.body) || req.body.updateDoc == null) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.post(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.UpdateOne = async (req, res) => {
    try {
        if (!("query" in req.body) || req.body.query == null || !("updateDoc" in req.body) || req.body.updateDoc == null) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.put(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.UpdateMany = async (req, res) => {
    try {
        if (!("query" in req.body) || req.body.query == null || !("updateDoc" in req.body) || req.body.updateDoc == null) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.put(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.DeleteOne = async (req, res) => {
    try {
        if (!("query" in req.body) || (req.body.query == null)) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.put(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.DeleteMany = async (req, res) => {
    try {
        if (!("query" in req.body.payloadInfo) || req.body.payloadInfo.query == null) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.put(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }


        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.BulkWrite = async (req, res) => {
    try {
        if (!("operations" in req.body) || req.body.operations == null) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.post(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.IncrementField = async (req, res) => {
    try {
        if (!("query" in req.body) || (req.body.query == null || !("field" in req.body) || req.body.field == null || !("incrementBy" in req.body) || req.body.incrementBy == null)) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.put(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.SetField = async (req, res) => {
    try {
        if (!("query" in req.body) || (req.body.query == null || !("field" in req.body) || req.body.payloadInfo.field == null || !("value" in req.body) || req.body.value == null)) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.put(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.AddToArray = async (req, res) => {
    try {
        if (!("query" in req.body) || (req.body.query == null || !("field" in req.body) || req.body.field == null || !("value" in req.body) || req.body.value == null)) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.put(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.RemoveFromArray = async (req, res) => {
    try {
        if (!("query" in req.body) || (req.body.query == null || !("field" in req.body) || req.body.field == null || !("value" in req.body) || req.body.value == null)) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.put(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.UpdateArrayElement = async (req, res) => {
    try {
        if (!("query" in req.body) || req.body.query == null || !("field" in req.body) || req.body.field == null || !("oldValue" in req.body) || req.body.oldValue == null || !("newValue" in req.body) || req.body.newValue == null) {
            return res.status(400).send({"error": "invalid input"});
        }

        var queryResponse = await axios.put(req.body.functionUrl, req.body);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};