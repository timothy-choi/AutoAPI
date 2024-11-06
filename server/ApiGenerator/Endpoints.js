const mongoose = require('mongoose');

const EndpointsModel = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true,
        unique: true
    },
    ProjectName: {
        type: String,
        required: true,
        unique: true
    },
    EndpointType: {
        type: String,
        required: true
    },
    EndpointVersion: {
        type: String,
        required: false
    },
    CreatedAt: {
        type: Date,
        required: true,
    },
    DidUpdate: {
        type: Boolean
    },
    UpdatedAt: {
        type: Date,
        required: false,
    },
    UpdatedBy: {
        type: String,
        required: false
    },
    EndpointHeaders: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    EndpointModels: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    EndpointDatabases: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    EndpointServerlessFunction: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    EndpointsCreationFile: {
        type: Map,
        of: String
    },
    AllEndpointRequestInfo: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    AllEndpointImplementationInfo: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    EndpointStatus: {
        type: String,
        required: false
    },
    EndpointDescription: {
        type: String,
        required: true
    },
    EndpointResponseSchema: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    EndpointResponseExample: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    LastAccessedAt: {
        type: Date,
        required: false
    },
    EndpointDependencies: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    EndpointsVersionHistory: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    }
});

const EndpointsDefinition = mongoose.model('EndpointsModel', EndpointsModel);

module.exports = EndpointsDefinition;