const Model = require('./Model');

modules.GetModelById = async (modelId) => {
    var model = await Model.findByPk(modelId);

    return model;
};

module.GetModelByName = async (modelName) => {
    var model = await Model.findOne({ where: { ModelName: modelName }});

    return model;
}

module.CreateModel = async (modelInfo) => {
    var model = await GetModelByName(modelInfo.name);

    if (model) {
        throw new Error("model already exists");
    }

    model = await Model.Create({ModelName: modelInfo.name,  ModelCreatedAt: Date.now(), ModelCreatedBy: modelInfo.createdBy, ModelAttributes: modelInfo.modelAttributes, ModelDescription: modelInfo.ModelDescription});

    return model;
}

module.DeleteModel = async (modelId) => {
    try {
        var model = await GetModelById(modelId);

        if (!model) {
            throw new Error('Model does not exist');
        } 

        await model.destroy();

    } catch (error) {
        throw new Error('could not delete model');
    }
}