const apiGatewayHelper = require('./ApiGatewayHelper');

exports.createApi = async (req, res) => {
    try {
        const authHeader = req.headers['authorization']; 

        if (!authHeader) {
            return res.status(401).send('Authorization header is missing');
        }

        const token = authHeader.split(' ')[1]; 

        if (!token) {
            return res.status(401).send('Token is missing');
        }

        const result = await apiGatewayHelper.createApi(token, req.body.apiName, req.body.url, req.body.realApiName);

        return res.status(201).send(result);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.getApiInfo = async (req, res) => {
    try {
        const authHeader = req.headers['authorization']; 

        if (!authHeader) {
            return res.status(401).send('Authorization header is missing');
        }

        const token = authHeader.split(' ')[1]; 

        if (!token) {
            return res.status(401).send('Token is missing');
        }

        const result = await apiGatewayHelper.getApiInfo(token, req.projectId, req.apiId);

        return res.status(200).send(result);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createApiConfig = async (req, res) => {
    try {
        const authHeader = req.headers['authorization']; 

        if (!authHeader) {
            return res.status(401).send('Authorization header is missing');
        }

        const token = authHeader.split(' ')[1]; 

        if (!token) {
            return res.status(401).send('Token is missing');
        }

        const result = await apiGatewayHelper.createApiConfig(token, req.body.apiName, req.body.configId, req.body.projectId, req.body.location, req.body.openApiSpecUrl, req.body.serviceAcct);

        return res.status(201).send(result);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};