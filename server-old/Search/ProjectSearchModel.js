const Joi = require('joi');

const projectSearchSchema = Joi.Object({
    projectId: Joi.string().required(),
    projectName: Joi.string().required(),
    projectOwner: Joi.string().required()
});

module.exports = projectSearchSchema;