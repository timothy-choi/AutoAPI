const ApiSecurityAuthService = require('./ApiSecurityService');

exports.getApiSecurityById = async (req, res) => {
    try {
        var apiSecurity = await ApiSecurityAuthService.getApiSecurityAuthById(req.apiSecurityId);

        return res.status(200).send(apiSecurity);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.getApiSecurityByName = async (req, res) => {
    try {
        var apiSecurity = await ApiSecurityAuthService.getApiSecurityAuthBySecurityName(req.apiSecurityName);

        return res.status(200).send(apiSecurity);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createApiSecurity = async (req, res) => {
    try {
        var apiSecurity = await ApiSecurityAuthService.createApiSecurityAuth(req.body);

        return res.status(201).send(apiSecurity);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.setApiKeyInfo = async (req, res) => {
    try {
        await ApiSecurityAuthService.setApiKeyInfo(req.apiSecurityId, req.body.apiKeyInfo);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.setOAuthInfo = async (req, res) => {
    try {
        await ApiSecurityAuthService.setOAuthInfo(req.apiSecurityId, req.body.oAuthInfo);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.setJWTInfo = async (req, res) => {
    try {
        await ApiSecurityAuthService.setJwtInfo(req.apiSecurityId, req.body.jwtInfo);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.setRateLimit = async (req, res) => {
    try {
        await ApiSecurityAuthService.setRateLimit(req.apiSecurityId, req.body.rateLimit);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.setAdditionalSecurityConfig = async (req, res) => {
    try {
        await ApiSecurityAuthService.setAdditionalSecurityConfig(req.apiSecurityId, req.body.additionalSecurityConfig);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteApiSecurity = async (req, res) => {
    try {
        await ApiSecurityAuthService.deleteApiSecurityAuth(req.apiSecurityId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};
