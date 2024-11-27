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

exports.SetFunctionVersion = async (req, res) => {
    try {
        await ServerlessFunctionService.SetFunctionVersion(req.serverlessFunctionId, req.versionValue);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddRoute = async (req, res) => {
    try {
        await ServerlessFunctionService.AddRoute(req.serverlessFunctionId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveRoute = async (req, res) => {
    try {
        await ServerlessFunctionService.RemoveRoute(req.serverlessFunctionId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditRoute = async (req, res) => {
    try {
        await ServerlessFunctionService.EditRoute(req.serverlessFunctionId, req.serverlessFunctionRouteInfoId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetStatus = async (req, res) => {
    try {
        await ServerlessFunctionService.EditStatus(req.serverlessFunctionId, req.statusValue);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetHealthStatus = async (req, res) => {
    try {
        await ServerlessFunctionService.EditHealthStatus(req.serverlessFunctionId, req.healthStatusValue);

        return res.status(200).json(null);
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

