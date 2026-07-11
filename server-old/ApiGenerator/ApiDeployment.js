const { DataTypes, Sequelize } = require('sequelize');
const sequelize = require('../config/database');

const ApiDeployment = sequelize.define('ApiDeployment', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    ProjectId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    Version: {
        type: DataTypes.STRING, 
        allowNull: true
    },
    BaseUrl: {
        type: DataTypes.STRING, 
        allowNull: true
    },
    CreatedAt: {
        type: DataTypes.DATE,
        defaultValue: Sequelize.NOW
    },
    UpdatedAt: {
        type: DataTypes.DATE,
        defaultValue: Sequelize.NOW
    },
    Status: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: () => ({})
    },
    Environment: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: () => ({})
    },
    DeployedAt: {
        type:  DataTypes.DATE,
        defaultValue: Sequelize.NOW
    },
    DeployedDuration: {
        type: DataTypes.INTEGER,
        allowNull: true,
    },
    DeploymentTarget: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: () => ({})
    },
    DeploymentMetadata: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: () => ({})
    },
    RollbackInfo: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: () => ({})
    },
    AllApiModels: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    AllApiEndpoints: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    AllApiDatabases: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    ApiAuthentication: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: () => ({})
    },
    ApiDocumentation: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: () => ({})
    },
    ApiMonitoring: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: () => ({})
    },
    ApiGateway: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: () => ({})
    },
    DeploymentLogs: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: [],
    }
});

module.exports = ApiDeployment;