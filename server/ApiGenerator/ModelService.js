const Model = require('./Model');

modules.GetModelById = async (modelId) => {
    var model = await Model.findByPk(modelId);

    return model;
};

module.GetModelByName = async (modelName) => {
    var model = await Model.findOne({ where: { ModelName: modelName }});

    return model;
}