module.exports = {
  extends: './node_modules/gts/',
  rules: {
    // https://johnnyreilly.com/typescript-5-importsnotusedasvalues-error-eslint-consistent-type-imports#eslint-and-typescript-eslintconsistent-type-imports-to-the-rescue
    '@typescript-eslint/consistent-type-imports': 'error', // the replacement of "importsNotUsedAsValues": "error"
    '@typescript-eslint/no-unused-vars': 'warn', // fine with a wwarn here
  },
};
