const mongoose = require('mongoose');

const ApiDocumentationSchema = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true
    },
    Description: {
        type: String,
        required: true
    },
    Version: {
        type: String,
        required: false
    },
    Endpoints: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    Authentication: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    RateLimit: {
        type: Number,
        required: false
    },
    DocumentationInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    }
});

const ApiDocumentation = mongoose.model('ApiDocumentationModel', ApiDocumentationSchema);

module.exports = ApiDocumentation;