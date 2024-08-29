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
        allowNull: false
    },
    GroupJoined: {
        type: DataTypes.BOOLEAN,
        allowNull: true
    },
    GroupIds: {
        type: DataTypes.ARRAY(DataTypes.UUID),
        allowNull: true,
        defaultValue: [],
    },
    AllCollaborators: {
        type: DataTypes.ARRAY(DataTypes.JSONB),  
        allowNull: false,
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
        allowNull: false,
        defaultValue: true,
    },
    ApiProjectsCreated: { //stores projects user either created by themselves or was the founder of the api in a group
        type: DataTypes.ARRAY(DateTypes.JSONB),
        allowNull: false,
        defaultValue: []
    },
    ApiProjectsContributed: { //stores projects that the user helped in a group project (not a leader) or recieved a request to helper another user with the their api (not a group)
        type: DataTypes.ARRAY(DateTypes.JSONB),
        allowNull: false,
        defaultValue: [],
    },
    CurrentApiProjects: { //any project the user is currently working on
        type: DateTypes.ARRAY(DateTypes.JSONB),
        allowNull: false,
        defaultValue: [],
    },
    ApiProjectsWithAccess: { //projects that user is not either a group member or even helped but private projets that the user can use
        type: DateTypes.ARRAY(DateTypes.JSONB),
        allowNull: false,
        defaultValue: [],
    },
    ApiProjectsViewHistory: {  //all projects that uwer has at least seen before in order of access
        type: DateTypes.ARRAY(DateTypes.JSONB),
        allowNull: false,
        defaultValue: [],
    }
});

module.exports = User;