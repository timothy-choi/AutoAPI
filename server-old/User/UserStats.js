const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const UserStats = sequelize.define('UserStats', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    }, 
    UserId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    ProjectsCreated: {
        type: DataTypes.ARRAY(DataTypes.UUID),
        allowNull: true,
    },
    TotalNumberOfApisCreated: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: 0,
    },
    TotalNumberOfEndpointsCreated: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: 0,
    },
    TotalNumberOfApisViewed: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: 0,
    },
    TotalNumberOfApisExecuted: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: 0,
    },
    AverageErrorRate: {
        type: DataTypes.DOUBLE,
        allowNull: true,
        defaultValue: 0.0,
    },
    AverageResponseTime: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: 0,
    },
    MostPopularApi: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    },
    NumberOfContributors: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: 0,
    },
    ProjectsUsedStats: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    ProjectOwnedManagementInfo: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    }
});

module.exports = UserStats;