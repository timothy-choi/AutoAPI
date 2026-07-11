const ApiDocumentationService = require('./ApiDocumentationService');

exports.GetApiDocumentationById = async (req, res) => {
    try {
        var documentation = await ApiDocumentationService.getApiDocumentationById(req.documentationId);

        return res.status(200).send(documentation);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.GetApiDocumentationByProjectId = async (req, res) => {
    try {
        var documentation = await ApiDocumentationService.getApiDocumentationByProjectId(req.projectId);

        return res.status(200).send(documentation);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.CreateApiDocumentation = async (req, res) => {
    try {
        var documentation = await ApiDocumentationService.createApiDocumentation(req.body);

        return res.status(201).send(documentation);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.EditApiDocumentationVersion = async (req, res) => {
    try {
       await ApiDocumentationService.editApiDocumentationVersion(req.documentationId, req.version);

       return res.status(200).send(null);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.EditApiDocumentationEndpoints = async (req, res) => {
    try {
       await ApiDocumentationService.setEndpoints(req.documentationId, req.body);

       return res.status(200).send(null);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.SetApiDocumentationAuth = async (req, res) => {
    try {
       await ApiDocumentationService.setAuthentication(req.documentationId, req.body);

       return res.status(200).send(null);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.EditApiDocumentationRateLimit = async (req, res) => {
    try {
       await ApiDocumentationService.setRateLimit(req.documentationId, req.rateLimit);

       return res.status(200).send(null);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.EditApiDocumentationInfo = async (req, res) => {
    try {
       await ApiDocumentationService.setDocumentationInfo(req.documentationId, req.body);

       return res.status(200).send(null);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.DeleteApiDocumentation = async (req, res) => {
    try {
        await ApiDocumentationService.deleteApiDocumentation(req.documentationId);

        return res.status(200).send(null);
    } catch (error) {
        throw new Error(error.message);
    }
};