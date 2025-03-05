const { RowStateEnum } = require('@google-cloud/bigtable');
const OpenAIHelper = require('./OpenAIHelper');

exports.generateChatResponse = async (req, res) => {
    try {
        response = OpenAIHelper.generateChatResponse(req.body.prompt, req.body.model);

        return res.status(201).send(response);
    } catch (err) {
        return res.status(500).send(err.message);
    }
};

exports.generateResponse = async (req, res) => {
    try {
        response = OpenAIHelper.generateResponse(req.body.prompt, req.body.model);

        return res.status(201).send(response);
    } catch (err) {
        return res.status(500).send(err.message);
    }
};