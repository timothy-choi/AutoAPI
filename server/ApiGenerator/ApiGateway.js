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
    }
});

const ApiGateway = mongoose.model('ApiGatewayModel', ApiGatewaySchema);

module.exports = ApiGateway;