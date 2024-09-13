const Joi = require('joi');

const userSearchSchema = Joi.Object({
    userId: Joi.string().required(),
    userName: Joi.string().required()
});

module.exports = userSearchSchema;