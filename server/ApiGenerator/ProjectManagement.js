const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const ProjectManagement = sequelize.define('ProjectManagement', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    ProjectId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    GroupId: { //only if the project is group based
        type: DataTypes.UUID,
        allowNull: true,
    },
    UserId: { //only for solo projects
        type: DataTypes.UUID,
        allowNull: true,
    },
    ProjectStatsInfo: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    },
    CreatedAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: false
    },
    UpdatedAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: true
    },
    AllUsers: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    ApiMonitoringLog: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    ErrorLog: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
});


module.exports = ProjectManagement;