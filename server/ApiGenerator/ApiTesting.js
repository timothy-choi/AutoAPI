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
    },
    CreatedAt: {
        type: Date,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
    }
});

const PostmanApiCollectionSchema = mongoose.Schema({
    TestingId: {
        type: String,
        required: true
    },
    PostmanCollectionId: {
        type: String,
        required: true
    },
    ProjectId: {
        type: String,
        required: true
    },
    EnvironmentId: {
        type: String,
        required: true
    },
    CreatedAt: {
        type: Date,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
    }
})

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
    },
    CreatedAt: {
        type: Date,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
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
    AllDatabases: {
        type: [mongoose.Schema.Types.Mixed],
        required: true
    },
    AllServerlessFunctions: {
        type: [mongoose.Schema.Types.Mixed],
        required: true
    },
    PostmanApiInfo: {
        type: PostmanApiInfoSchema,
        required: true
    },
    PostmanApiCollection: {
        type: PostmanApiCollectionSchema,
        required: true
    },
    PostmanEnvironmentInfo: {
        type: PostmanApiEnvironmentSchema,
        required: true
    },
    CreatedAt: {
        type: Date,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
    },
    Status: {
        type: String,
        required: false
    },
    TestingScore: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    }
});

const ApiTesting = mongoose.model('ApiTesting', ApiTestingSchema);

module.exports = ApiTesting;

