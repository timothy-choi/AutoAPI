const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const User = sequelize.define('User', {
    id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    userAuthId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    username: {
        type: DataTypes.STRING,
        allowNull: false,
        unique: true,
    },
    email: {
        type: DataTypes.STRING,
        allowNull: false,
        unique: true,
    },
    createdAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: false
    },
    LastActiveAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: true
    },
    GroupJoined: {
        type: DataTypes.BOOLEAN,
        allowNull: true
    },
    PastGroupIds: {
        type: DataTypes.ARRAY(DataTypes.UUID),
        allowNull: true,
        defaultValue: [],
    },
    CurrentGroupIds: {
        type: DataTypes.ARRAY(DataTypes.UUID),
        allowNull: true,
        defaultValue: [],
    },
    AllCollaborators: {
        type: DataTypes.ARRAY(DataTypes.JSONB),  
        allowNull: true,
        defaultValue: [],  
    },
    NotificationsOn: {
        type: DataTypes.BOOLEAN,
        allowNull: true
    },
    NotificationType: {
        type: DataTypes.STRING,
        allowNull: true
    },
    IsAvailable: {
        type: DataTypes.BOOLEAN,  
        allowNull: true,
        defaultValue: true,
    },
    ApiProjectsCreated: { //stores projects user either created by themselves or was the founder of the api in a group
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    ApiProjectsContributed: { //stores projects that the user helped in a group project (not a leader) or recieved a request to helper another user with the their api (not a group)
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: [],
    },
    CurrentApiProjects: { //any project the user is currently working on
        type: DateTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: [],
    },
    ApiProjectsWithAccess: { //projects that user is not either a group member or even helped but private projets that the user can use
        type: DateTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: [],
    },
    ApiProjectsViewHistory: {  //all projects that uwer has at least seen before in order of access
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: [],
    },
    CloudProviderInfo: { //information for each cloud provider that user wants to use (ex. AWS, GCP, Azure, etc)
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: [],
    },
    ActivityLog: { // log to track user's actions (like generated an API, added an endpoint, etc)
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: [],
    }, 
    CloudProviderDefault: {  //default cloud provider that user an choose or determined based on usage
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {},
    },
    AllReceivedUserProjectInvitations: { //all invitations that the user has recieved from other users for help on their APIs
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: [],
    },
    AllSentUserProjectInvitations: { //same ideas as last one, but this time user is sending invitations for help
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: [],
    },
    AllSentGroupJoinRequests: { // all requests from user to join groups
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: [],
    },
    AllUserProjectViewRequests: { //list of all user request for access to user's private project(s)
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: [],
    },
    UserGithubInfo: {  //user's github info
        type: DataTypes.JSONB,
        allowNull: false,
        defaultValue: {},
    },
    UserStatsId: {
        type: DataTypes.UUID,
        allowNull: true,
    },
    UserDescription: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    UserTags: {
        type: DataTypes.ARRAY(DataTypes.STRING),
        allowNull: true,
        defaultValue: [],
    },
    NotificationAccountId: {
        type: DataTypes.UUID,
        allowNull: true,
    },
    Followers: {
        type: DataTypes.ARRAY(DateTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    Following: {
        type: DataTypes.ARRAY(DateTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    MessagingAccountId: {
        type: DataTypes.UUID,
        allowNull: true,
    }
});

module.exports = User;