const axios = require('axios');

module.exports = async function (context, req) {
    try {
        const delayMinutes = req.body.delayMinutes;
        const delayMilliseconds = delayMinutes * 60000;

        await axios.post(req.body.httpFunctionUri, req.body.payload);

        setTimeout(async () => {
            await axios.post(req.body.triggerUri);
        }, delayMilliseconds);

        context.res = {
            status: 200,
            body: null
        };
    } catch (error) {
        context.res = {
            status: 500,
            body: error.message
        };
    }
};