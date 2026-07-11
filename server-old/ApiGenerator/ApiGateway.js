const mongoose = require('mongoose');

const ApiGatewaySchema = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true
    },
    EndpointsId: {
        type: String,
        required: true
    },
    CreatedBy: {
        type: String,
        required: true
    },
    CreatedAt: {
        type: Date,
        default: Date.now
    },
    UpdatedAt: {
        type: Date,
        default: Date.now
    },
    UpdatedBy: {
        type: String
    },
    Routes: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    ApiGatewayInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    Metrics: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    SecurityInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    UsageInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    DeploymentStatus: {
        type: String,
        required: false
    },
    Subscription: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    Throttling: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    }
});

const ApiGateway = mongoose.model('ApiGatewayModel', ApiGatewaySchema);

module.exports = ApiGateway;