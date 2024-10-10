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
    CreatedAt: {
        type: Date,
        required: true,
    },
    UpdatedAt: {
        type: Date,
        required: false,
    },
    UpdatedBy: {
        type: String,
        required: false
    },
});

const EndpointsDefinition = mongoose.model('EndpointsModel', EndpointsModel);

module.exports = EndpointsDefinition;