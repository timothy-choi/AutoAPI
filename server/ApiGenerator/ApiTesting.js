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
    CreatedBy: {
        type: String,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
    },
    UpdatedBy: {
        type: String,
        required: false
    },
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
    PostmanInfoId: {
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
    CreatedBy: {
        type: String,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
    },
    UpdatedBy: {
        type: String,
        required: false
    },
    ApiTestingFolders: {
        type: [PostmanApiFolderSchema],
        required: false
    }
});

const PostmanApiFolderSchema = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true
    },
    TestingId: {
        type: String,
        required: true
    },
    MainCollectionId: {
        type: String,
        required: true
    },
    CreatedAt: {
        type: Date,
        required: true
    },
    CreatedBy: {
        type: String,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
    },
    UpdatedBy: {
        type: String,
        required: false
    },
    TestCases: {
        type: [ApiTestCase],
        required: false
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
    },
    CreatedAt: {
        type: Date,
        required: true
    },
    CreatedBy: {
        type: String,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
    },
    UpdatedBy: {
        type: String,
        required: false
    }
});

const ApiTestRequest = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true,
        unique: true
    },
    TestingId: {
        type: String,
        requird: true
    },
    MainCollectionId: {
        type: String,
        required: true
    },
    FolderId: {
        type: String,
        required: false
    },
    ModelInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: true
    },
    EndpointInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: true
    },
    DatabaseInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: true
    },
    ServerlessFunctionInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: true
    },
    RequestHeaders: {
        type: mongoose.Schema.Types.Mixed,
        required: true
    },
    RequestBody: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    RequestParamInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    OrderNumber: {
        type: Number,
        required: true
    },
    CreatedAt: {
        type: Date,
        required: true
    },
    CreatedBy: {
        type: String,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
    },
    UpdatedBy: {
        type: String,
        required: false
    }
});

const ApiExpectedResponse = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true,
        unique: true
    },
    TestingId: {
        type: String,
        requird: true
    },
    MainCollectionId: {
        type: String,
        required: true
    },
    FolderId: {
        type: String,
        required: false
    },
    ExpectedCode: {
        type: Number,
        required: true
    },
    ExpectedResponseBody: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    ExpectedError: {
        type: Boolean,
        required: true
    },
    ExpectedErrorMessage: {
        type: String,
        required: false
    },
    TestRequest: {
        type: mongoose.Schema.Types.Mixed,
        required: true
    },
    CreatedAt: {
        type: Date,
        required: true
    },
    CreatedBy: {
        type: String,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
    },
    UpdatedBy: {
        type: String,
        required: false
    }
});

const ApiTestResponse = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true,
        unique: true
    },
    TestingId: {
        type: String,
        requird: true
    },
    MainCollectionId: {
        type: String,
        required: true
    },
    FolderId: {
        type: String,
        required: false
    },
    ResponseCode: {
        type: Number,
        required: true
    },
    ResponseBody: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    TestRequest: {
        type: mongoose.Schema.Types.Mixed,
        required: true
    },
    Failed: {
        type: Boolean,
        required: true
    },
    Error: {
        type: Boolean,
        required: true
    },
    ErrorMessage: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    ExpectedResponse: {
        type: mongoose.Schema.Types.Mixed,
        required: true
    }
})

const ApiTestCase = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true,
        unique: true
    },
    TestingId: {
        type: String,
        required: true,
    },
    MainCollectionId: {
        type: String,
        required: true,
    },
    FolderId: {
        type: String,
        required: false,
    },
    ModelsUsed: {
        type: [mongoose.Schema.Types.Mixed],
        required: true,
    },
    EndpointsUsed: {
        type: [mongoose.Schema.Types.Mixed],
        required: true,
    },
    DatabasesUsed: {
        type: [mongoose.Schema.Types.Mixed],
        required: true,
    },
    ServerlessFunctionsUsed: {
        type: [mongoose.Schema.Types.Mixed],
        required: true,
    },
    AllTestCaseRequests: {
        type: [ApiTestRequest], 
        required: true
    },
    AllExpectedResponses: {
        type: [ApiExpectedResponse],
        required: true
    },
    AllResponses: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    CreatedAt: {
        type: Date,
        required: true
    },
    CreatedBy: {
        type: String,
        required: true
    },
    UpdatedAt: {
        type: Date,
        required: false
    },
    UpdatedBy: {
        type: String,
        required: false
    }
});

const ApiTestRun = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true,
    },
    TestingId: {
        type: String,
        required: true,
    },
    MainCollectionId: {
        type: String,
        required: true,
    },
    FolderId: {
        type: String,
        required: false,
    },
    TestRequests: {
        type: [ApiTestRequest],
        required: true
    },
    ExpectedResponses: {
        type: [ApiExpectedResponse],
        required: true
    },
    ActualResponses: {
        type: [ApiTestResponse],
        required: false
    },
    TestCaseResult: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    TestCaseDurationTime: {
        type: Number,
        required: false
    },
    Status: {
        type: String,
        required: false
    }
});

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
    CurrentTestingScore: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    ApiTestingFolders: {
        type: [PostmanApiFolderSchema],
        required: false
    },
    AllTestCases: {
        type: [ApiTestCase],
        required: false
    },
    AllTestCaseRuns: {
        type: [ApiTestRun],
        required: false
    },
    TestingChangeLog: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    }
});

const ApiTesting = mongoose.model('ApiTesting', ApiTestingSchema);

module.exports = ApiTesting;

