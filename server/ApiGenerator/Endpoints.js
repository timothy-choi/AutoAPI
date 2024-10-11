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
    }
});

const EndpointsDefinition = mongoose.model('EndpointsModel', EndpointsModel);

module.exports = EndpointsDefinition;