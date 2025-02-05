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