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
        allowNull: false,
        defaultValue: {},
    },
    PrivateMode: {
        type: DataTypes.BOOLEAN,
        allowNull: false,
        default: false,
    },
    UserJoinRequests: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: false,
        default: [],
    },
    UserViewRequests: { //only if group is private
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: false,
        default: [],
    },
    GroupActivityLog: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: false,
        default: [],
    },
    CreatedAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: false
    },
    Tags: {
        type: DataTypes.ARRAY(DataTypes.STRING),
        allowNull: false,
        default: [],
    },
    LastUpdatedAt: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW,
        allowNull: false
    }
});

module.exports = Group;