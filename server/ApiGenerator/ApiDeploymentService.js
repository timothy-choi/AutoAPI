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

exports.setVersion = async (deploymentId, version) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.Version = version;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setBaseUrl = async (deploymentId, baseUrl) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.BaseUrl = baseUrl;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setStatus = async (deploymentId, status) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.Status = status;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setEnvironment = async (deploymentId, environment) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.Environment = environment;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDeployedAt = async (deploymentId) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.DeployedAt = Date.now;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDeployedDuration = async (deploymentId, deployedDuration) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.DeployedDuration = deployedDuration;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDeployedTarget = async (deploymentId, deployedTarget) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.DeploymentTarget = deployedTarget;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDeployedMetadata = async (deploymentId, deployedMetadata) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.DeploymentMetadata = deployedMetadata;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDeployedRollbackInfo = async (deploymentId, rollbackInfo) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.RollbackInfo = rollbackInfo;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.deleteApiDeployment = async (deploymentId) => {
    try {
        var deployment = await this.getApiDeploymentById(deploymentId);

        if (!deployment) {
            throw new Error("Instance does not exist");
        }

        await deployment.destroy();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};