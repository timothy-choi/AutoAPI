const axios = require('axios');

exports.HttpTriggerMetricsFunction = async (message, context) => {
    try {
        const payload = JSON.parse(Buffer.from(message.data, 'base64').toString());

        var response = await axios.post(payload.httpFunctionUri, payload);

        context.res = {
            status: response.status,
            body: response.data
        };
    } catch (error) {
        context.res = {
            status: 500,
            body: error.message
        };
    }
};