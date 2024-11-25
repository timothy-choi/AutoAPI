const EndpointService = require('./EndpointService');

exports.GetEndpointsById = async (req, res) => {
    try {
        const endpoints = await EndpointService.GetEndpointsById(req.endpointId);

        return res.status(200).body(endpoints);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.GetEndpointsByProjectName = async (req, res) => {
    try {
        const endpoints = await EndpointService.GetEndpointsByProject(req.projectName);

        return res.status(200).body(endpoints);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.CreateEndpoints = async (req, res) => {
    try {
        const endpoints = await EndpointService.CreateEndpoints(req.body);

        return res.status(201).body(endpoints);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddEndpointHeader = async (req, res) => {
    try {
        await EndpointService.AddEndpointHeader(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveEndpointHeader = async (req, res) => {
    try {
        await EndpointService.RemoveEndpointHeader(req.endpointId, req.endpointHeaderId, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditEndpointHeader = async (req, res) => {
    try {
        await EndpointService.EditEndpointHeader(req.endpointId, req.endpointHeaderId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.DeleteEndpoints = async (req, res) => {
    try {
        await EndpointService.DeleteEndpoints(req.endpointId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}