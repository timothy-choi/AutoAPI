const axios = require('axios')

exports.ExecuteQuery = async (req, res) => {
    try {
        var queryInfo = {
            connectionInfo: req.body.connectionInfo,
            query: req.body.query,
            params: req.body.params
        };

        var queryResponse = await axios.post("/sqlOperations/query/" + req.database, queryInfo);

        if (queryResponse.status != 201) {
            return res.status(400).send({"error": queryResponse.json()});
        }

        return res.status(201).send({"queryResult": queryResponse.json()});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DefineDBSchema = async (req, res) => {
    try {
        var queryInfo = {
            connectionInfo: req.body.connectionInfo,
            query: req.body.query
        };

        var schemaResponse = await axios.post("/sqlOperations/schema/" + req.database, queryInfo);

        if (schemaResponse.status != 201) {
            return res.status(401).send(schemaResponse.json());
        }

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.InsertIntoDB = async (req, res) => {
    try {
        var queryInfo = {
            connectionInfo: req.body.connectionInfo,
            query: req.body.query,
            params: req.body.params
        };

        var queryResponse = await axios.post("/sqlOperations/insert/" + req.database, queryInfo);

        if (queryResponse.status != 201) {
            return res.status(400).send({"error": queryResponse.json()});
        }

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.ModifyInDB = async (req, res) => {
    try {
        var queryInfo = {
            connectionInfo: req.body.connectionInfo,
            query: req.body.query,
            params: req.body.params
        };

        var modifyResponse = await axios.put("/sqlOperations/modify/" + req.database, queryInfo);

        if (modifyResponse.status != 200) {
            return res.status(400).send({"error": queryResponse.json()});
        }

        return res.status(200).send({"modifyResponse": modifyResponse.json()});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteInDB = async (req, res) => {
    try {
        var queryInfo = {
            connectionInfo: req.body.connectionInfo,
            query: req.body.query,
            params: req.body.params
        };

        var deleteResponse = await axios.put("/sqlOperations/remove/" + req.database, queryInfo);

        if (deleteResponse.status != 200) {
            return res.status(400).send({"error": deleteResponse.json()});
        }

        return res.status(200).send({"deleteResponse": deleteResponse.json()});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

