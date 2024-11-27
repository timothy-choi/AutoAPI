const ServerlessFunctionService = require('./ApiServerlessFunctionService');

exports.GetServerlessFunctionById = async (req, res) => {
    try {
        var serverlessFunction = await ServerlessFunctionService.GetServerlessFunctionById(req.serverlessFunctionId);

        return res.status(200).body(serverlessFunction);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.GetServerlessFunctionByFunctionName = async (req, res) => {
    try {
        var serverlessFunction = await ServerlessFunctionService.GetServerlessFunctionByFunctionName(req.serviceFunctionName);

        return res.status(200).body(serverlessFunction);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.CreateServerlessFunction = async (req, res) => {
    try {
        var serverlessFunction = await ServerlessFunctionService.CreateServerlessFunction(req.body);

        return res.status(201).json(serverlessFunction);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.DeleteServerlessFunction = async (req, res) => {
    try {
        await ServerlessFunctionService.DeleteServerlessFunction(req.serverlessFunctionId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

