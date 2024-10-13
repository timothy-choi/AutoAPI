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
    }
});

module.exports = ProjectStats;