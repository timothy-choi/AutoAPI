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