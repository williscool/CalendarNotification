"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.useSafeAreaInsets = exports.useSafeAreaFrame = exports.initialWindowMetrics = exports.SafeAreaView = exports.SafeAreaProvider = exports.SafeAreaInsetsContext = exports.SafeAreaFrameContext = void 0;

var _react = _interopRequireDefault(require("react"));

var _reactNative = require("react-native");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

// Define default insets
const DEFAULT_INSETS = {
  top: 0,
  right: 0,
  bottom: 0,
  left: 0
}; // Define default frame

const DEFAULT_FRAME = {
  x: 0,
  y: 0,
  width: 0,
  height: 0
}; // Create context objects

const SafeAreaInsetsContext = /*#__PURE__*/_react.default.createContext(DEFAULT_INSETS);

exports.SafeAreaInsetsContext = SafeAreaInsetsContext;

const SafeAreaFrameContext = /*#__PURE__*/_react.default.createContext(DEFAULT_FRAME);

exports.SafeAreaFrameContext = SafeAreaFrameContext; // Create provider component

const SafeAreaProvider = ({
  children,
  initialMetrics,
  ...props
}) => {
  return /*#__PURE__*/_react.default.createElement(_reactNative.View, props, children);
};

exports.SafeAreaProvider = SafeAreaProvider; // Create hook functions

const useSafeAreaInsets = () => DEFAULT_INSETS;

exports.useSafeAreaInsets = useSafeAreaInsets;

const useSafeAreaFrame = () => DEFAULT_FRAME;

exports.useSafeAreaFrame = useSafeAreaFrame; // Create view component

const SafeAreaView = ({
  children,
  ...props
}) => {
  return /*#__PURE__*/_react.default.createElement(_reactNative.View, props, children);
};

exports.SafeAreaView = SafeAreaView; // Initial metrics for optimization

const initialWindowMetrics = {
  insets: DEFAULT_INSETS,
  frame: DEFAULT_FRAME
};

exports.initialWindowMetrics = initialWindowMetrics; 