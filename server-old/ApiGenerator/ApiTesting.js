const mongoose = require('mongoose');

const PostmanApiInfoSchema = mongoose.Schema({
    TestingId: {
        type: String,
        required: true
    },
    Name: {
        type: String,
        required: true,
        unique: true
    },
    BaseUrl: {
        type: String,
        required: false,
        unique: true
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
    IsActive: {
        type: Boolean,
        required: true
    },
    LastTestRun: {
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
    },
    NumberOfRequests: {
        type: Number,
        required: false
    },
    AverageResponseTime: {
        type: Number,
        required: false
    },
    TotalTestCount: {
        type: Number,
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
    },
    Status: {
        type: String,
        required: true
    },
    FolderDescription: {
        type: String,
        required: true
    },
    IsActive: {
        type: Boolean,
        required: true
    },
    LastTextExecuted: {
        type: Date,
        required: false
    },
    AverageResponseTime: {
        type: Number, 
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
        required: true
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
    AuthInfo: {
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
    },
    Timeout: {
        type: Number,
        required: true
    },
    IsCritical: {
        type: Boolean,
        required: true
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
        required: true
    },
    MainCollectionId: {
        type: String,
        required: true
    },
    FolderId: {
        type: String,
        required: false
    },
    HeadersExpected: {
        type: Boolean,
        required: true
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
    },
    IsCritical: {
        type: Boolean,
        required: true
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
    ResponseHeaders: {
        type: mongoose.Schema.Types.Mixed,
        required: true
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
    },
    ResponseTime: {
        type: Number,
        required: true
    },
    WarningMessages: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    AdditionalInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
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
    },
    Description: {
        type: String,
        required: true
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
    },
    RunAt: {
        type: Date,
        required: true
    },
    RunBy: {
        type: String,
        required: true
    }
});

const ApiTestingSchema = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true,
        unique: true
    },
    ApiVersion: {
        type: String,
        required: true
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

