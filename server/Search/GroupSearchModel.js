const Joi = require('joi');

const groupSearchSchema = Joi.Object({
    groupId: Joi.string().required(),
    groupName: Joi.string().required(),
    projectId: Joi.string().required()
});

module.exports = groupSearchSchema;