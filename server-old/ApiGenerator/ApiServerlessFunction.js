const mongoose = require('mongoose');

const ApiServerlessFunctionSchema = mongoose.Schema({
    FunctionName: {
        type: String,
        required: true,
        unique: true,
    },
    Runtime: {
        type: String,
        required: true,
    },
    Version: {
        type: String,
        required: true,
    },
    Routes: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    CreatedAt: {
        type: Date,
        default: Date.now
    },
    UpdatedAt: {
        type: Date,
        default: Date.now
    },
    Status: {
        type: String,
        required: false
    },
    HealthStatus: {
        type: String,
        required: false
    },
    Timeout: {
        type: Number,
        required: true,
        default: 30, 
    },
    DeployedUrl: {
        type: String,
        required: false
    },
    EnvironmentVariables: {
        type: Map,
        of: mongoose.Schema.Types.Mixed
    }, 
    Logs: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    ServerlessFunctionInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    MetricsInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    ErrorHandlingInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    ServerlessFunctionVersionLog: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    IntegrationInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    ServerlessFunctionUsageInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    ServerlessFunctionBillingInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    }
});

const ApiServerlessFunction = mongoose.model('ApiServerlessFunctionModel', ApiServerlessFunctionSchema);

module.exports = ApiServerlessFunction;

