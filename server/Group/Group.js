const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const Group = sequelize.define('Group', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    GroupName: {
        type: DataTypes.STRING,
        allowNull: false,
        unique: true,
    },
    GroupUsers: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: false,
    }, 
    CanJoin: {
        type: DataTypes.BOOLEAN,
        allowNull: false,
        default: true,
    },
    GroupProject: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {},
    },
    PrivateMode: {
        type: DataTypes.BOOLEAN,
        allowNull: false,
        default: false,
    },
    UserJoinRequests: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        default: [],
    },
    UserViewRequests: { //only if group is private
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        default: [],
    },
    GroupActivityLog: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        default: [],
    },
    CreatedAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: false
    },
    GroupTags: {
        type: DataTypes.ARRAY(DataTypes.STRING),
        allowNull: true,
        default: [],
    },
    GroupDescription: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    LastUpdatedAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: true
    },
    GroupChatroomId: {
        type: DataTypes.UUID,
        allowNull: true
    },
});

module.exports = Group;