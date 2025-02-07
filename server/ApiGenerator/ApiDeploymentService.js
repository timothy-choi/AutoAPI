const ApiDeployment = require('./ApiDeployment');

exports.getApiDeploymentById = async (deploymentId) => {
    try {
        var uuidVal = mongoose.types.ObjectId(documentationId);

        var deployment = await ApiDeployment.findById(uuidVal);

        return deployment;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.getApiDeploymentByProjectId = async (projectId) => {
    try {
        var deployment = await ApiDeployment.findOne({ where: { ProjectId: projectId } });

        return deployment;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.createApiDeployment = async (deploymentInfo) => {
    try {
        var deployment = await this.getApiDeploymentByProjectId(deploymentInfo.projectId);

        if (deployment) {
            throw new Error("Instance already exists");
        }

        deployment = new ApiDeployment({
            ProjectId: ProjectId
        });

        await deployment.save();

        return deployment;
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.deleteApiDeployment = async (deploymentId) => {
    try {
        var deployment = await this.getApiDeploymentById(documentationId);

        if (!deployment) {
            throw new Error("Instance does not exist");
        }

        await deployment.destroy();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};