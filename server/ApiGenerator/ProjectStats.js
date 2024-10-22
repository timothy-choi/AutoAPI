const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const ProjectStats = sequelize.define('ProjectStats', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    ProjectId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    NumberOfViews: {
        type: DataTypes.NUMBER,
        allowNull: true,
        defaultValue: 0
    },
    NumberOfApiCallsSent: {
        type: DataTypes.NUMBER,
        allowNull: true,
        defaultValue: 0
    },
    ApiEndpointUsage: { //usage info about api endpoints
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        default: [],
    },
    ContributorStats: { //stats about contributors fore this project
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        default: [],
    },
    AverageResponseTime: {
        type: DataTypes.NUMBER,
        allowNull: true
    },
    UptimePercentage: {
        type: DataTypes.DOUBLE,
        allowNull: true
    },
    ErrorRate: {
        type: DataTypes.DOUBLE,
        allowNull: true
    },
    TopErrorCodes: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true
    },
    GeoDistribution: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true
    },
    PeakTrafficTime: {
        type: DataTypes.TIME,
        allowNull: true
    },
    MaxResponseTime: {
        type: DataTypes.FLOAT,
        allowNull: true
    },
    MinResponseTime: {
        type: DataTypes.FLOAT,
        allowNull: true
    },
    TotalDataTransferred: {
        type: DataTypes.BIGINT, 
        allowNull: true,
        defaultValue: 0
    },
    RequestTypeDistribution: {
        type: DataTypes.JSONB, 
        allowNull: true,
        defaultValue: {}
    },    
    ActiveUsers: {
        type: DataTypes.JSONB, 
        allowNull: true,
        defaultValue: {}
    },
    LatencyByRegion: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    }
});

module.exports = ProjectStats;