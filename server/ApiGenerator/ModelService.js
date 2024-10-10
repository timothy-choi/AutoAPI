const Model = require('./Model');

module.exports = async (modelId) => {
    var model = await Model.findByPk(modelId);

    return model;
};

module.exports = async (modelName) => {
    var model = await Model.findOne({ where: { ModelName: modelName }});

    return model;
}