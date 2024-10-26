const mongoose = require('mongoose');

const PostmanApiInfoSchema = mongoose.Schema({
    TestingId: {
        type: String,
        required: true
    },
    Name: {
        type: String,
        required: true
    },
    BaseUrl: {
        type: String,
        required: false
    },
    MainCollectionId: {
        type: String,
        required: true
    }
});

const PostmanApiEnvironmentSchema = mongoose.Schema({
    TestingId: {
        type: String,
        required: true
    },
    ProjectId: {
        type: String,
        required: true
    },
    Name: {
        type: String,
        required: true
    },
    Variables: {
        type: [mongoose.Schema.Types.Mixed],
        required: true
    }
})

const ApiTestingSchema = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true,
        unique: true
    },
    AllModels: {
        type: [mongoose.Schema.Types.Mixed],
        required: true
    },
    AllEndpoints: {
        type: [mongoose.Schema.Types.Mixed],
        required: true
    },
    PostmanApiInfo: {
        type: PostmanApiInfoSchema,
        required: true
    },
    PostmanEnvironmentInfo: {
        type: PostmanApiEnvironmentSchema,
        required: true
    }
});

const ApiTesting = mongoose.model('ApiTesting', ApiTestingSchema);

module.exports = ApiSecurityAuth;

