const mongoose = require('mongoose');

const ApiSecurityAuthSchema = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true,
        unique: true
    },
    SecurityAuthName: {
        type: String,
        required: true
    },
    AuthenticationType: {
        type: String,
        required: true
    },
    ApiKeyInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    OAuthInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    JwtInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    RateLimit: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    AdditionalSecurityConfig: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    }
});

const ApiSecurityAuth = mongoose.model('ApiSecurityAuth', ApiSecurityAuthSchema);

module.exports = ApiSecurityAuth;