const ApiSecurity = require('./ApiSecurityAuth');

exports.getApiSecurityAuthById = async (securityId) => {
    try {
        var securityAuthId = mongoose.types.ObjectId(securityId);

        return await ApiSecurity.findById(securityAuthId);
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.getApiSecurityAuthBySecurityName = async (securityName) => {
    try {
        return await ApiSecurity.findOne({SecurityAuthName: securityName});   
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.createApiSecurityAuth = async (securityAuthInfo) => {
    try {
        var securityAuth = await this.getApiSecurityAuthBySecurityName(securityAuthInfo.securityName);

        if (securityAuth) {
            throw new Error("Security already exists");
        }

        securityAuth = await ApiSecurity.Create({
            ProjectId: securityAuthInfo.projectId,
            SecurityName: securityAuthInfo.securityName,
            AuthenticationType: securityAuthInfo.authenticationType
        });

        return securityAuth;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.setApiKeyInfo = async (securityId, apiKeyInfo) => {
    try {
        var securityAuth = await getApiSecurityAuthById(securityId);

        if (!securityAuth) {
            throw new Error("Security does not exist");
        }

        securityAuth.ApiKeyInfo = apiKeyInfo;

        await securityAuth.save();
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.setOAuthInfo = async (securityId, oAuthInfo) => {
    try {
        var securityAuth = await getApiSecurityAuthById(securityId);

        if (!securityAuth) {
            throw new Error("Security does not exist");
        }

        securityAuth.OAuthInfo = oAuthInfo;

        await securityAuth.save();
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.setJwtInfo = async (securityId, jwtInfo) => {
    try {
        var securityAuth = await getApiSecurityAuthById(securityId);

        if (!securityAuth) {
            throw new Error("Security does not exist");
        }

        securityAuth.JwtInfo = jwtInfo;

        await securityAuth.save();
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.setRateLimit = async (securityId, rateLimit) => {
    try {
        var securityAuth = await getApiSecurityAuthById(securityId);

        if (!securityAuth) {
            throw new Error("Security does not exist");
        }

        securityAuth.RateLimit = rateLimit;

        await securityAuth.save();
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.setAdditionalSecurityConfig = async (securityId, additionalSecurityConfig) => {
    try {
        var securityAuth = await getApiSecurityAuthById(securityId);

        if (!securityAuth) {
            throw new Error("Security does not exist");
        }

        securityAuth.AdditionalSecurityConfig = additionalSecurityConfig;

        await securityAuth.save();
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deleteApiSecurityAuth = async (securityId) => {
    try {
        var securityAuth =  await getApiSecurityAuthById(securityId);

        if (!securityAuth) {
            throw new Error("Security does not exist");
        }

        await securityAuth.destroy();
    } catch (error) {
        throw new Error(error.message);
    }
}