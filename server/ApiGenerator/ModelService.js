const Model = require('./Model');

exports.GetModelById = async (modelId) => {
    var model = await Model.findByPk(modelId);

    return model;
};

exports.GetModelByName = async (modelName) => {
    var model = await Model.findOne({ where: { ModelName: modelName }});

    return model;
}

exports.CreateModel = async (modelInfo) => {
    var model = await GetModelByName(modelInfo.name);

    if (model) {
        throw new Error("model already exists");
    }

    model = await Model.Create({ModelName: modelInfo.name,  ModelCreatedAt: Date.now(), ModelCreatedBy: modelInfo.createdBy, ModelAttributes: modelInfo.modelAttributes, ModelDescription: modelInfo.ModelDescription});

    return model;
}

exports.AddModelAttribute = async (modelId, modelAttributeInfo, username) => {
    try {
        var model = await GetModelById(modelId);

        if (!model) {
            throw new Error('Model does not exist');
        } 

        model.ModelAttributes.add(modelAttributeInfo);

        model.ModelDidUpdate = true;

        model.ModelUpdatedAt = Date.now();

        model.ModelUpdatedBy = username;

        await model.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.DeleteModelAttribute = async (modelId, modelAttributeId, username) => {
    try {
        var model = await GetModelById(modelId);

        if (!model) {
            throw new Error('Model does not exist');
        } 

        model.ModelAttributes.filter(modelAttribute => modelAttribute.id != modelAttributeId);

        model.ModelDidUpdate = true;

        model.ModelUpdatedAt = Date.now();

        model.ModelUpdatedBy = username;

        await model.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditModelAttribute = async (modelId, modelAttributeId, modelAttributeInfo, username) => {
    try {
        var model = await GetModelById(modelId);

        if (!model) {
            throw new Error('Model does not exist');
        } 

        var index = model.ModelAttributes.findIndex(modelAttributes = modelAttributes.id != modelAttributeId);

        model.ModelAttributes.splice(index, 1);

        model.ModelAttributes.splice(index, 0, modelAttributeInfo);

        model.ModelDidUpdate = true;

        model.ModelUpdatedAt = Date.now();

        model.ModelUpdatedBy = username;

        await model.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditModelDescription = async (modelId, modelDesc) => {
    try {
        var model = await GetModelById(modelId);

        if (!model) {
            throw new Error('Model does not exist');
        } 

        model.ModelDescription = modelDesc;

        model.ModelDidUpdate = true;

        model.ModelUpdatedAt = Date.now();

        model.ModelUpdatedBy = username;

        await model.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.DeleteModel = async (modelId) => {
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