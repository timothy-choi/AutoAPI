const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const Project = sequelize.define('Project', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    ProjectApiName: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    ProjectApiType: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    IsPrivate: {
        type: DataTypes.BOOLEAN,
        allowNull: false,
    },
    ProjectStatus: {
        type: DataTypes.STRING,
        allowNull: true,
    },
    ProjectHealthStatus: {
        type: DataTypes.STRING,
        allowNull: true,
    },
    GroupProject: {
        type: DataTypes.BOOLEAN,
        allowNull: false,
    },
    GroupId: { //only if a project is a group project
        type: DataTypes.UUID,
        allowNull: true,
    },
    UserId: { //only if a project is maintained by a single user
        type: DataTypes.UUID,
        allowNull: true,
    },
    CreatedAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: false
    },
    ModifiedAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: true
    },
    GithubUrl: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: "",
    },
    IsAvailable: {
        type: DataTypes.BOOLEAN,
        allowNull: true,
        defaultValue: false,
    },
    ProjectUpdates: { //list of "announcements" that describe updates/changes to api
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    AllProjectModels: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    AllEndpoints: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    },
    AllDatabases: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    ApiGateway: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    },
    ApiServerlessFunctions: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    ApiSecurityAuth: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    },
    AllProjectFileBucket: {
        type: DataTypes.STRING,
        allowNull: true
    },
    ProjectActivityLog: { //for projects with no groups
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        default: [],
    },
    ProjectUserRequestsHistory: { //history of all requests by project owner to any user
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        default: [],
    },
    AwaitingUserProjectUserRequests: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        default: [],
    },
    AssignedUserProjectUserRequests: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        default: [],
    },
    ProjectContributors: { //only for projects with no groups
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        default: [],
    },
    ProjectViewRequestsRecieved: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        default: [],
    },
    ProjectStatsId: {
        type: DataTypes.UUID,
        allowNull: false,
    }
});

module.exports = Project;