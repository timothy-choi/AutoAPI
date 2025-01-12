const ApiGatewayHelper = require('./ApiGatewayHelper');

exports.getApiGateway = async (req, res) => {
    try {
        const apiGateway = await ApiGatewayHelper.getApiGateway(req.body.apiGatewayId, req.body.userCredentials, req.body.userRegion);

        return res.status(201).send(apiGateway);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};