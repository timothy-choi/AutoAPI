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
});

module.exports = Project;