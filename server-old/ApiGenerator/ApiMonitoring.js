const mongoose = require('mongoose');

const ApiLogSchema = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true
    },
    MonitoringId: {
        type: String,
        required: true
    },
    ApiEndpoint: {
        type: String,
        required: true
    },
    Method: {
        type: String,
        required: true
    },
    RequestBody: {
        type: mongoose.Schema.Types.Mixed, 
        required: false
    },
    ResponseBody: {
        type: mongoose.Schema.Types.Mixed, 
        required: false
    },
    StatusCode: {
        type: Number,
        required: true
    },
    ResponseTime: {
        type: Number, 
        required: true
    },
    CreatedAt: {
        type: Date,
        default: Date.now 
    },
    Error: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    }
});

const ApiMonitoringSchema = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true
    },
    EndpointsId: {
        type: String,
        required: false
    },
    MonitoringServiceInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    CreatedAt: {
        type: Date,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
    },
    MonitoringMetricsInfo: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    MonitoringLogs: {
        type: [ApiLogSchema],
        required: false
    }
});

const ApiMonitoring = mongoose.model('ApiMonitoring', ApiMonitoringSchema);

const ApiSchema = mongoose.model('ApiLogSchema', ApiLogSchema);

module.exports = ApiMonitoring, ApiSchema;