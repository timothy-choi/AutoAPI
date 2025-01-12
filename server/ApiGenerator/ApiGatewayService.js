const ApiGateway = require('./ApiGateway');

exports.getApiGatewayById = async (gatewayId) => { 
    try {
        var uuidVal = mongoose.types.ObjectId(gatewayId);

        var apiGateway = await ApiGateway.findById(uuidVal);

        return apiGateway;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.getApiGatewayByProjectId = async (projectId) => {
    try {
        var apiGateway = await ApiGateway.findOne({ where: { ProjectId: projectId } });

        return apiGateway;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.createApiGateway = async (ProjectId, EndpointsId, CreatedBy) => {
    try {
        var apiGateway = await this.getApiGatewayByProjectId(ProjectId);

        if (apiGateway) {
            throw new Error("Instance already exists");
        }

        apiGateway = new ApiGateway({
            ProjectId: ProjectId,
            EndpointsId: EndpointsId,
            CreatedBy: CreatedBy,
            CreatedAt: Date.now()
        });

        await apiGateway.save();

        return apiGateway;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.addRoute = async (gatewayId, route, updatedBy) => {
    try {
        var apiGateway = await this.getApiGatewayById(gatewayId);

        if (!apiGateway) {
            throw new Error("Instance does not exist");
        }

        apiGateway.Routes.push(route);

        apiGateway.UpdatedAt = Date.now();

        apiGateway.UpdatedBy = updatedBy;

        await apiGateway.save();
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.deleteRoute = async (gatewayId, routeId, updatedBy) => {
    try {
        var apiGateway = await this.getApiGatewayById(gatewayId);

        if (!apiGateway) {
            throw new Error("Instance does not exist");
        }

        var routeIndex = apiGateway.Routes.findIndex(route => route.id === routeId);

        if (routeIndex === -1) {
            throw new Error("Route does not exist");
        }

        apiGateway.Routes.splice(routeIndex, 1);

        apiGateway.UpdatedAt = Date.now();

        apiGateway.UpdatedBy = updatedBy;

        await apiGateway.save();
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.updateRouter = async (gatewayId, routeId, route, updatedBy) => {
    try {
        var apiGateway = await this.getApiGatewayById(gatewayId);

        if (!apiGateway) {
            throw new Error("Instance does not exist");
        }

        var routeIndex = apiGateway.Routes.findIndex(route => route.id === routeId);

        if (routeIndex === -1) {
            throw new Error("Route does not exist");
        }

        apiGateway.Routes[routeIndex] = route;

        apiGateway.UpdatedAt = Date.now();

        apiGateway.UpdatedBy = updatedBy;

        await apiGateway.save();
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.editUsageInfo = async (gatewayId, usageInfo, updatedBy) => {
    try {
        var apiGateway = await this.getApiGatewayById(gatewayId);

        if (!apiGateway) {
            throw new Error("Instance does not exist");
        }

        apiGateway.UsageInfo = usageInfo;

        apiGateway.UpdatedAt = Date.now();

        apiGateway.UpdatedBy = updatedBy;

        await apiGateway.save();
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.setDeploymentStatus = async (gatewayId, deploymentStatus, updatedBy) => {
    try {
        var apiGateway = await this.getApiGatewayById(gatewayId);

        if (!apiGateway) {
            throw new Error("Instance does not exist");
        }

        apiGateway.DeploymentStatus = deploymentStatus;

        apiGateway.UpdatedAt = Date.now();

        apiGateway.UpdatedBy = updatedBy;

        await apiGateway.save();
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.setThrottling = async (gatewayId, throttling, updatedBy) => {
    try {
        var apiGateway = await this.getApiGatewayById(gatewayId);

        if (!apiGateway) {
            throw new Error("Instance does not exist");
        }

        apiGateway.Throttling = throttling;

        apiGateway.UpdatedAt = Date.now();

        apiGateway.UpdatedBy = updatedBy;

        await apiGateway.save();
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.setSubscription = async (gatewayId, subscription, updatedBy) => {
    try {
        var apiGateway = await this.getApiGatewayById(gatewayId);

        if (!apiGateway) {
            throw new Error("Instance does not exist");
        }

        apiGateway.Subscription = subscription;

        apiGateway.UpdatedAt = Date.now();

        apiGateway.UpdatedBy = updatedBy;

        await apiGateway.save();
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.deleteApiGateway = async (gatewayId) => {
    try {
        var apiGateway = await this.getApiGatewayById(gatewayId);

        if (!apiGateway) {
            throw new Error("Instance does not exist");
        }

        await apiGateway.destroy();
    } catch (error) {
        throw new Error("Error:", error.message);
    }
}