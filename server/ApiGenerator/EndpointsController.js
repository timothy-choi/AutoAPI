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

exports.AddEndpointModel = async (req, res) => {
    try {
        await EndpointService.AddEndpointModel(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveEndpointModel = async (req, res) => {
    try {
        await EndpointService.RemoveEndpointModel(req.endpointId, req.endpointModelId, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditEndpointModel = async (req, res) => {
    try {
        await EndpointService.EditEndpointModel(req.endpointId, req.endpointModelId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddEndpointDatabase = async (req, res) => {
    try {
        await EndpointService.AddEndpointDatabase(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveEndpointDatabase = async (req, res) => {
    try {
        await EndpointService.RemoveEndpointDatabase(req.endpointId, req.endpointDatabaseId, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditEndpointDatabase = async (req, res) => {
    try {
        await EndpointService.EditEndpointDatabase(req.endpointId, req.endpointDatabaseId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddEndpointServerlessFunction = async (req, res) => {
    try {
        await EndpointService.AddEndpointServerlessFunctions(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveEndpointServerlessFunction = async (req, res) => {
    try {
        await EndpointService.RemoveEndpointServerlessFunctions(req.endpointId, req.endpointServerlessFunctionId, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditEndpointServerlessFunction = async (req, res) => {
    try {
        await EndpointService.EditEndpointServerlessFunction(req.endpointId, req.endpointServerlessFunctionId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.ModifyEndpointCreationFile = async (req, res) => {
    try {
        await EndpointService.ModifyEndpointCreationFile(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddEndpointRequestInfo = async (req, res) => {
    try {
        await EndpointService.AddEndpointRequestInfo(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveEndpointRequestInfo = async (req, res) => {
    try {
        await EndpointService.RemoveEndpointRequestInfo(req.endpointId, req.endpointRequestInfoId, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditEndpointRequestInfo = async (req, res) => {
    try {
        await EndpointService.EditEndpointRequestInfo(req.endpointId, req.endpointRequestInfoId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddEndpointImplementationInfo = async (req, res) => {
    try {
        await EndpointService.AddEndpointImplementationInfo(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveEndpointImplementationInfo = async (req, res) => {
    try {
        await EndpointService.RemoveEndpointImplementationInfo(req.endpointId, req.endpointImplementationInfoId, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditEndpointImplementationInfo = async (req, res) => {
    try {
        await EndpointService.EditEndpointImplementationInfo(req.endpointId, req.endpointImplementationInfoId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddApiEndpointsStatus = async (req, res) => {
    try {
        await EndpointService.AddApiEndpointStatus(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditApiEndpointsStatus = async (req, res) => {
    try {
        await EndpointService.EditApiEndpointStatus(req.endpointId, req.endpointStatusId, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.ModifyEndpointsDescription = async (req, res) => {
    try {
        await EndpointService.ModifyEndpointsDescription(req.endpointId, req.body.endpointsDescription, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddEndpointResponseSchema = async (req, res) => {
    try {
        await EndpointService.AddEndpointResponseSchema(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveEndpointResponseSchema = async (req, res) => {
    try {
        await EndpointService.RemoveApiEndpointResponseSchema(req.endpointId, req.endpointResponseSchemaId, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditEndpointResponseSchema = async (req, res) => {
    try {
        await EndpointService.EditApiEndpointResponseSchema(req.endpointId, req.endpointResponseSchemaId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddEndpointResponseExample = async (req, res) => {
    try {
        await EndpointService.AddEndpointResponseExample(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveEndpointResponseExample = async (req, res) => {
    try {
        await EndpointService.RemoveApiEndpointResponseExample(req.endpointId, req.endpointResponseExampleId, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditEndpointResponseExample = async (req, res) => {
    try {
        await EndpointService.EditApiEndpointResponseExample(req.endpointId, req.endpointResponseExampleId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetLastAccessedAt = async (req, rres) => {
    try {
        await EndpointService.SetLastAccessedAt(req.endpointId, req.body.lastAccessedAt);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddEndpointDependency = async (req, res) => {
    try {
        await EndpointService.AddEndpointDependencies(req.endpointId, req.endpointResponseSchemaId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveEndpointDependency = async (req, res) => {
    try {
        await EndpointService.RemoveApiEndpointDependency(req.endpointId, req.endpointDependencyId, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditEndpointDependency = async (req, res) => {
    try {
        await EndpointService.EditApiEndpointDependency(req.endpointId, req.endpointDependencyId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.ModifyEndpointMetrics = async (req, res) => {
    try {
        await EndpointService.ModifyEndpointMetrics(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddEndpointVersionHistory = async (req, reds) => {
    try {
        await EndpointService.AddEndpointVersionHistory(req.endpointId, req.body, req.username);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.ModifyServerlessFunctionFile = async (req, res) => {
    try {
        await EndpointService.ModifyServerlessFunctionFile(req.endpointId, req.body, req.username);

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