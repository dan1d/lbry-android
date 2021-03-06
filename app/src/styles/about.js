import { StyleSheet } from 'react-native';
import Colors from './colors';

const aboutStyle = StyleSheet.create({
  scrollContainer: {
    paddingTop: 16,
    paddingBottom: 16
  },
  row: {
    marginBottom: 1,
    backgroundColor: '#f9f9f9',
    padding: 16,
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'space-between'
  },
  title: {
    color: Colors.LbryGreen,
    fontSize: 24,
    fontFamily: 'Metropolis-SemiBold',
    marginLeft: 12,
    marginRight: 12,
    marginBottom: 8
  },
  paragraph: {
    fontFamily: 'Metropolis-Regular',
    fontSize: 16,
    lineHeight: 24,
    marginLeft: 12,
    marginRight: 12,
    marginBottom: 24
  },
  links: {
    marginLeft: 12,
    marginRight: 12,
    marginBottom: 12
  },
  link: {
    color: Colors.LbryGreen,
    fontFamily: 'Metropolis-Regular',
    fontSize: 16,
    marginBottom: 24
  },
  col: {
    alignSelf: 'stretch'
  },
  releaseInfoTitle: {
    fontFamily: 'Metropolis-Regular',
    marginLeft: 12,
    marginRight: 12,
    marginBottom: 12,
    fontSize: 20
  },
  text: {
    fontFamily: 'Metropolis-SemiBold',
    fontSize: 15
  },
  valueText: {
    fontFamily: 'Metropolis-Regular',
    textAlign: 'right',
    fontSize: 15
  },
  lineValueText: {
    fontFamily: 'Metropolis-Regular',
    fontSize: 15
  }
});

export default aboutStyle;
