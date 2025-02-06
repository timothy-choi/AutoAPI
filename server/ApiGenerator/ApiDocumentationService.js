const ApiDocumentation = require('./ApiDocumentation');

exports.getApiDocumentationById = async (documentationId) => {
    try {
        var uuidVal = mongoose.types.ObjectId(documentationId);

        var documentation = await ApiDocumentation.findById(uuidVal);

        return documentation;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.getApiDocumentationByProjectId = async (projectId) => {
    try {
        var documentation = await ApiDocumentation.findOne({ where: { ProjectId: projectId } });

        return documentation;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.createApiDocumentation = async (docInfo) => {
    try {
        var documentation = await getApiDocumentationByProjectId(docInfo.projectId);

        if (documentation) {
            throw new Error("Instance already exists");
        }

        documentation = new ApiDocumentation({
            ProjectId: ProjectId,
            Description: docInfo.description
        });

        await documentation.save();

        return documentation;
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.editApiDocumentationVersion = async (documentationId, version) => {
    try {
        var documentation = await getApiDocumentationById(documentationId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.Version = version;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setEndpoints = async (documentationId, endpoints) => {
    try {
        var documentation = await getApiDocumentationById(documentationId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.Endpoints = endpoints;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setAuthentication = async (documentationId, authenticationInfo) => {
    try {
        var documentation = await getApiDocumentationById(documentationId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.Authentication = authenticationInfo;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setRateLimit = async (documentationId, rateLimit) => {
    try {
        var documentation = await getApiDocumentationById(documentationId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.RateLimit = rateLimit;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDocumentationInfo = async (documentationId, docInfo) => {
    try {
        var documentation = await getApiDocumentationById(documentationId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.DocumentationInfo = docInfo;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.deleteApiDocumentation = async (documentationId) => {
    try {
        var documentation = await getApiDocumentationById(documentationId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        await documentation.destroy();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};